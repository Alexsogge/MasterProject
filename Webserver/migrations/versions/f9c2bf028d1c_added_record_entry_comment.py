"""added record entry comment

Revision ID: f9c2bf028d1c
Revises: d4af52777f90
Create Date: 2022-04-20 17:32:06.901941

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'f9c2bf028d1c'
down_revision = 'd4af52777f90'
branch_labels = None
depends_on = None


def upgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    op.create_table('record_entry_comment',
    sa.Column('id', sa.Integer(), nullable=False),
    sa.Column('based_recording_id', sa.Integer(), nullable=True),
    sa.Column('file_name', sa.String(), nullable=True),
    sa.Column('comment', sa.String(), nullable=True),
    sa.ForeignKeyConstraint(['based_recording_id'], ['recording.id'], ),
    sa.PrimaryKeyConstraint('id')
    )
    # ### end Alembic commands ###


def downgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    op.drop_table('record_entry_comment')
    # ### end Alembic commands ###
