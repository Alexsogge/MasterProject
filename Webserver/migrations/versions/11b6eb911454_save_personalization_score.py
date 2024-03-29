"""save personalization score

Revision ID: 11b6eb911454
Revises: 68bf0a071433
Create Date: 2022-03-30 14:10:54.968669

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = '11b6eb911454'
down_revision = '68bf0a071433'
branch_labels = None
depends_on = None


def upgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table('personalization', schema=None) as batch_op:
        batch_op.add_column(sa.Column('false_diff_relative', sa.Float(), nullable=True))
        batch_op.add_column(sa.Column('correct_diff_relative', sa.Float(), nullable=True))

    # ### end Alembic commands ###


def downgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table('personalization', schema=None) as batch_op:
        batch_op.drop_column('correct_diff_relative')
        batch_op.drop_column('false_diff_relative')

    # ### end Alembic commands ###
